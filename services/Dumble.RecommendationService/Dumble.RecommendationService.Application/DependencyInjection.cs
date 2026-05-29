using Dumble.RecommendationService.Application.Behaviors;
using Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;
using FluentValidation;
using Microsoft.Extensions.DependencyInjection;

namespace Dumble.RecommendationService.Application;

public static class DependencyInjection
{
    public static IServiceCollection AddApplication(this IServiceCollection services)
    {
        services.AddMediatR(cfg =>
        {
            cfg.RegisterServicesFromAssembly(typeof(DependencyInjection).Assembly);
            cfg.AddOpenBehavior(typeof(ValidationBehavior<,>));
        });

        // Validators are registered explicitly to avoid pulling the FluentValidation
        // DI-extensions package; add new ones here as features land.
        services.AddScoped<IValidator<RecordInteractionCommand>, RecordInteractionCommandValidator>();

        // Injected clock so handlers stay deterministic and unit-testable.
        services.AddSingleton(TimeProvider.System);

        return services;
    }
}
