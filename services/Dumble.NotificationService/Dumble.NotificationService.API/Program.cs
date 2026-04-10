using System.Text;
using FastEndpoints;
using FastEndpoints.Swagger;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.SignalR;
using Microsoft.IdentityModel.Tokens;
using Dumble.NotificationService.Application;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.API.Hubs;
using Dumble.NotificationService.Infrastructure;

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
builder.Services.AddScoped<INotificationHubService, NotificationHubService>();

// FastEndpoints
builder.Services.AddFastEndpoints();
builder.Services.SwaggerDocument(o =>
{
    o.DocumentSettings = s =>
    {
        s.Title = "Dumble Notification Service API";
        s.Version = "v1";
    };
});

// JWT Authentication
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"],
            ValidAudience = builder.Configuration["Jwt:Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(builder.Configuration["Jwt:Secret"] ?? "default-dev-secret-key-min-32-chars!!"))
        };

        // Allow SignalR to receive the token via query string
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

app.MapHub<NotificationHub>("/hubs/notifications");

app.Run();
