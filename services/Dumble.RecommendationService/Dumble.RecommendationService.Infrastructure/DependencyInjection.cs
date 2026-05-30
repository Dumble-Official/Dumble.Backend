using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Outbox;
using Dumble.RecommendationService.Infrastructure.Authentication;
using Dumble.RecommendationService.Infrastructure.Outbox;
using Dumble.RecommendationService.Infrastructure.Persistence;
using Dumble.RecommendationService.Infrastructure.Recombee;
using Dumble.SharedKernel.Contracts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Dumble.RecommendationService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("RecommendationDb")
            ?? "Host=localhost;Port=5432;Database=dumble_recommendations;Username=dumble;Password=dumble123";

        services.AddDbContext<RecommendationDbContext>(options =>
            options.UseNpgsql(connectionString).UseSnakeCaseNamingConvention());

        services.AddScoped<IInteractionOutboxWriter, InteractionOutboxWriter>();

        // Current user is resolved from the validated JWT claims forwarded by the
        // gateway — no extra round-trip to the auth service.
        services.AddHttpContextAccessor();
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        AddRecombee(services, configuration);

        return services;
    }

    private static void AddRecombee(IServiceCollection services, IConfiguration configuration)
    {
        // Bind from the Recombee config section, falling back to flat RECOMBEE_* env vars
        // (the credentials live only in env / gitignored release/.env, never in appsettings).
        var section = configuration.GetSection(RecombeeOptions.SectionName);
        var options = new RecombeeOptions
        {
            DatabaseId = section["DatabaseId"] ?? configuration["RECOMBEE_DATABASE_ID"] ?? "",
            PrivateToken = section["PrivateToken"] ?? configuration["RECOMBEE_PRIVATE_TOKEN"] ?? "",
            Region = section["Region"] ?? configuration["RECOMBEE_REGION"] ?? "",
            FlushBatchSize = int.TryParse(section["FlushBatchSize"], out var batch) ? batch : 100,
            FlushIntervalSeconds = int.TryParse(section["FlushIntervalSeconds"], out var interval) ? interval : 5
        };
        services.AddSingleton(Options.Create(options));

        services.AddScoped<IOutboxFlushStore, EfOutboxFlushStore>();
        services.AddScoped<OutboxFlushProcessor>();

        if (options.IsConfigured)
        {
            services.AddSingleton<IRecombeeClient, RecombeeClientAdapter>();
            services.AddHostedService<OutboxFlushWorker>();
        }
        else
        {
            // No credentials — register a no-op client and skip the flush worker so the
            // service still boots locally (matches SocialService's optional-dependency style).
            services.AddSingleton<IRecombeeClient, DisabledRecombeeClient>();
        }
    }
}
