using Dumble.BundleManagementService.API.Authentication;
using Dumble.BundleManagementService.API.Errors;
using Dumble.BundleManagementService.Application;
using Dumble.BundleManagementService.Infrastructure;
using Dumble.BundleManagementService.Infrastructure.Persistence.Data;
using FastEndpoints;
using FastEndpoints.Swagger;
using FluentValidation;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using System.Net;

// The bundle's expiry date arrives from the client without a timezone kind
// (Unspecified). Npgsql rejects writing Unspecified DateTimes to 'timestamptz'
// columns, which 500'd bundle creation. This switch treats Unspecified as UTC,
// matching every other service's UTC-everywhere convention.
AppContext.SetSwitch("Npgsql.EnableLegacyTimestampBehavior", true);

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt => opt.AddServerHeader = false);

builder.Services.AddApplication().AddInfrastructure(builder.Configuration);
builder.Services.AddHttpContextAccessor();

var jwtSecret = builder.Configuration["Jwt:Secret"]
    ?? builder.Configuration["JWT_SECRET"]
    ?? throw new InvalidOperationException("JWT_SECRET env var is required");
var signingKey = new SymmetricSecurityKey(Convert.FromBase64String(jwtSecret));

builder.Services.AddAuthentication(opt =>
{
    opt.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    opt.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
}).AddJwtBearer(opt =>
{
    opt.RequireHttpsMetadata = builder.Environment.IsProduction();
    // Keep claim names as-issued by the JWT (sub, userId, displayName, etc.)
    // instead of remapping sub → ClaimTypes.NameIdentifier.
    opt.MapInboundClaims = false;
    opt.TokenValidationParameters = new TokenValidationParameters
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

builder.Services.AddFastEndpoints().SwaggerDocument();
builder.Services.AddValidatorsFromAssemblyContaining<Program>();

builder.Services.Configure<ForwardedHeadersOptions>(opt =>
{
    opt.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    // Only honour X-Forwarded-* from explicitly-listed proxies. Clearing
    // both lists without populating them tells ASP.NET to trust the headers
    // from anyone, so a direct hit to this service can spoof
    // X-Forwarded-Proto: https and trick the app into thinking an HTTP
    // request was secure. Provide the gateway's IPs/CIDR via
    // GATEWAY_TRUSTED_PROXIES (comma-separated) or fall back to the
    // dev-only loopback for local docker-compose.
    var configuredProxies = builder.Configuration["GATEWAY_TRUSTED_PROXIES"];
    if (string.IsNullOrWhiteSpace(configuredProxies))
    {
        opt.KnownNetworks.Add(new Microsoft.AspNetCore.HttpOverrides.IPNetwork(IPAddress.Loopback, 8));
        opt.KnownNetworks.Add(new Microsoft.AspNetCore.HttpOverrides.IPNetwork(IPAddress.Parse("172.16.0.0"), 12));
        opt.KnownNetworks.Add(new Microsoft.AspNetCore.HttpOverrides.IPNetwork(IPAddress.Parse("192.168.0.0"), 16));
        opt.KnownNetworks.Add(new Microsoft.AspNetCore.HttpOverrides.IPNetwork(IPAddress.Parse("10.0.0.0"), 8));
    }
    else
    {
        foreach (var raw in configuredProxies.Split(',', StringSplitOptions.RemoveEmptyEntries))
        {
            var entry = raw.Trim();
            if (IPAddress.TryParse(entry, out var ip))
            {
                opt.KnownProxies.Add(ip);
            }
        }
    }
});

builder.Services.AddHealthChecks()
    .AddDbContextCheck<BundleManagementDbContext>(name: "database");

var app = builder.Build();

// Provision the schema on startup. The service owns its database and ships no
// separate migration step (Postgres via EnsureCreated, like post-service), so the
// model's tables are created if they aren't there yet — in every environment.
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<BundleManagementDbContext>();
    db.Database.EnsureCreated();

    // EnsureCreated is a no-op once the table exists, so additive columns added
    // after the first deploy never appear. Apply them idempotently here so the
    // store/profile can show the seller. Safe on a fresh DB too (column already
    // present → IF NOT EXISTS is a no-op).
    db.Database.ExecuteSqlRaw(
        "ALTER TABLE \"Bundles\" ADD COLUMN IF NOT EXISTS \"OwnerUserId\" text;");
}

app.UseForwardedHeaders();
app.UseExceptionMapping();

if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapHealthChecks("/health/live");
app.MapHealthChecks("/health/ready");

app.UseFastEndpoints().UseSwaggerGen();

app.Run();

public partial class Program;
