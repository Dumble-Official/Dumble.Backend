using Dumble.RecommendationService.Infrastructure.Authentication;
using Dumble.SharedKernel.Contracts;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Dumble.RecommendationService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // Current user is resolved from the validated JWT claims forwarded by the
        // gateway — no extra round-trip to the auth service.
        services.AddHttpContextAccessor();
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        return services;
    }
}
