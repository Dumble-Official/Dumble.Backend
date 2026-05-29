using Dumble.RecommendationService.Infrastructure.Authentication;
using Dumble.RecommendationService.Infrastructure.Persistence;
using Dumble.SharedKernel.Contracts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Dumble.RecommendationService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("RecommendationDb")
            ?? "Host=localhost;Port=5432;Database=dumble_recommendations;Username=dumble;Password=dumble123";

        services.AddDbContext<RecommendationDbContext>(options =>
            options.UseNpgsql(connectionString).UseSnakeCaseNamingConvention());

        // Current user is resolved from the validated JWT claims forwarded by the
        // gateway — no extra round-trip to the auth service.
        services.AddHttpContextAccessor();
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        return services;
    }
}
