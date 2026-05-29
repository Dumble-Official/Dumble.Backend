using System.Net;
using Dumble.RecommendationService.API.Authentication;
using Dumble.RecommendationService.API.Errors;
using Dumble.RecommendationService.Application;
using Dumble.RecommendationService.Infrastructure;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt => opt.AddServerHeader = false);

builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

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

builder.Services.AddHealthChecks();

var app = builder.Build();

app.UseForwardedHeaders();
app.UseExceptionMapping();

app.UseAuthentication();
app.UseAuthorization();

app.MapHealthChecks("/health/live");
app.MapHealthChecks("/health/ready");

// NOTE: FastEndpoints (and its FluentValidation + Swagger wiring) is registered
// in the PR that introduces the first endpoint — its startup discovery throws
// when no endpoints exist yet, so it does not belong in the bare skeleton.

app.Run();

public partial class Program;
