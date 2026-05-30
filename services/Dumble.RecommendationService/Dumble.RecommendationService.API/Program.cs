using System.Net;
using Dumble.RecommendationService.API.Authentication;
using Dumble.RecommendationService.API.Errors;
using Dumble.RecommendationService.Application;
using Dumble.RecommendationService.Infrastructure;
using Dumble.RecommendationService.Infrastructure.Persistence;
using FastEndpoints;
using FastEndpoints.Swagger;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt => opt.AddServerHeader = false);

builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

builder.Services.AddFastEndpoints();

if (builder.Environment.IsDevelopment())
{
    builder.Services.SwaggerDocument(o =>
    {
        o.DocumentSettings = s =>
        {
            s.Title = "Dumble Recommendation Service API";
            s.Version = "v1";
        };
    });
}

// User tokens are signed by the auth service with a shared base64 HS256 key.
// The gateway has already validated + ban-checked the token; we re-validate the
// signature here as defence in depth and to populate the claims principal.
var jwtSecret = builder.Configuration["Jwt:Secret"]
    ?? builder.Configuration["JWT_SECRET"]
    ?? throw new InvalidOperationException("JWT_SECRET env var is required");
var signingKey = new SymmetricSecurityKey(Convert.FromBase64String(jwtSecret));

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.RequireHttpsMetadata = builder.Environment.IsProduction();
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = false,
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            RequireSignedTokens = true,
            IssuerSigningKey = signingKey,
            ClockSkew = TimeSpan.FromSeconds(30)
        };
    });

builder.Services.AddAuthorization();
builder.Services.AddTransient<IClaimsTransformation, RolesClaimsTransformation>();

// ForwardedHeaders trusted-proxy gating — see the other services for rationale.
builder.Services.Configure<ForwardedHeadersOptions>(opt =>
{
    opt.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    var trustedProxiesCsv = builder.Configuration["Gateway:TrustedProxies"];
    if (!string.IsNullOrWhiteSpace(trustedProxiesCsv))
    {
        opt.KnownProxies.Clear();
        opt.KnownNetworks.Clear();
        foreach (var raw in trustedProxiesCsv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (IPAddress.TryParse(raw, out var ip))
                opt.KnownProxies.Add(ip);
        }
    }
});

// Readiness includes the database; liveness must not (a DB blip should not get the
// container killed — only restarted out of the load-balancer rotation).
builder.Services.AddHealthChecks()
    .AddDbContextCheck<RecommendationDbContext>(name: "database", tags: new[] { "ready" });

var app = builder.Build();

// Apply migrations on startup in every environment: the service owns its schema
// and there is no separate migration step in the compose deployment. Fails fast if
// the database is unreachable, which is the desired behaviour behind depends_on.
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<RecommendationDbContext>();
    db.Database.Migrate();
}

app.UseForwardedHeaders();
app.UseExceptionMapping();

app.UseAuthentication();
app.UseAuthorization();

app.MapHealthChecks("/health/live", new HealthCheckOptions { Predicate = _ => false });
app.MapHealthChecks("/health/ready", new HealthCheckOptions { Predicate = check => check.Tags.Contains("ready") });

app.UseFastEndpoints(c => c.Errors.UseProblemDetails());

if (app.Environment.IsDevelopment())
{
    app.UseSwaggerGen();
}

app.Run();

public partial class Program;
