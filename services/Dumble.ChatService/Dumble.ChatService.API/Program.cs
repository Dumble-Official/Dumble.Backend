using FastEndpoints;
using FastEndpoints.Swagger;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.SignalR;
using Microsoft.IdentityModel.Tokens;
using Dumble.ChatService.Application;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.API.Hubs;
using Dumble.ChatService.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Application + Infrastructure DI
builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

// SignalR + Redis backplane
var signalR = builder.Services.AddSignalR();
var redisConnection = builder.Configuration.GetConnectionString("Redis");
if (!string.IsNullOrEmpty(redisConnection))
    signalR.AddStackExchangeRedis(redisConnection);

builder.Services.AddSingleton<IUserIdProvider, NameUserIdProvider>();
builder.Services.AddScoped<IChatHubService, ChatHubService>();

// FastEndpoints
builder.Services.AddFastEndpoints();
builder.Services.SwaggerDocument(o =>
{
    o.DocumentSettings = s =>
    {
        s.Title = "Dumble Chat Service API";
        s.Version = "v1";
    };
});

// JWT Authentication — validates HS256 signature using the shared secret.
var jwtSecret = builder.Configuration["Jwt:Secret"]
    ?? builder.Configuration["JWT_SECRET"]
    ?? throw new InvalidOperationException("JWT_SECRET env var is required");
var signingKey = new SymmetricSecurityKey(Convert.FromBase64String(jwtSecret));

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        // Keep claim names as-issued by the JWT (sub, userId, displayName, etc.)
        // instead of remapping sub → ClaimTypes.NameIdentifier.
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

        // SignalR negotiates over HTTP and upgrades to WebSocket; browsers
        // disallow custom headers on the upgrade so the token is passed via
        // query string. Clients should use a short-lived hub token issued by
        // POST /api/auth/hub-token to bound the leakage window.
        options.Events = new JwtBearerEvents
        {
            OnMessageReceived = context =>
            {
                var accessToken = context.Request.Query["access_token"];
                var path = context.HttpContext.Request.Path;
                if (!string.IsNullOrEmpty(accessToken) && path.StartsWithSegments("/hubs"))
                    context.Token = accessToken;

                return Task.CompletedTask;
            }
        };
    });

builder.Services.AddAuthorization();

var app = builder.Build();

// Middleware
app.UseAuthentication();
app.UseAuthorization();

app.UseFastEndpoints(c =>
{
    c.Errors.UseProblemDetails();
});

app.UseSwaggerGen();

app.MapHub<ChatHub>("/hubs/chat");

app.Run();
