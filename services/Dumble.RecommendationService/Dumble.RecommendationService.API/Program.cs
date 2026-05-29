using Dumble.RecommendationService.Application;
using Dumble.RecommendationService.Infrastructure;
using FastEndpoints;
using FastEndpoints.Swagger;
using FluentValidation;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt => opt.AddServerHeader = false);

builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

builder.Services.AddFastEndpoints();
builder.Services.AddValidatorsFromAssemblyContaining<Program>();

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

builder.Services.AddHealthChecks();

var app = builder.Build();

app.MapHealthChecks("/health/live");
app.MapHealthChecks("/health/ready");

app.UseFastEndpoints(c => c.Errors.UseProblemDetails());

if (app.Environment.IsDevelopment())
{
    app.UseSwaggerGen();
}

app.Run();

public partial class Program;
