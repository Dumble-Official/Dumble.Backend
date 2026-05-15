using System.Text;
using Dumble.BundleManagementService.Application;
using Dumble.BundleManagementService.Infrastructure;
using FastEndpoints;
using FastEndpoints.Swagger;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt =>
{
    opt.AddServerHeader = false;
});

builder.Services.AddApplication().AddInfrastructure(builder.Configuration);
builder.Services.AddHttpContextAccessor();

// JWT bearer auth — validates the same HS256 signature the Auth service issues.
// Defense in depth: the gateway is the primary entry point but we don't want a
// lateral attacker inside the cluster to forge a token by hitting us directly.
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
        // Auth service does not set issuer/audience — only validate what's set.
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
builder.Services.AddFastEndpoints().SwaggerDocument();

var app = builder.Build();

app.UseAuthentication();

app.UseAuthorization();

app.UseFastEndpoints().UseSwaggerGen();

app.Run();