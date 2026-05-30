using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Outbox;
using Dumble.RecommendationService.Infrastructure.Authentication;
using Dumble.RecommendationService.Infrastructure.ExternalServices;
using Dumble.RecommendationService.Infrastructure.Messaging.Consumers;
using Dumble.RecommendationService.Infrastructure.Outbox;
using Dumble.RecommendationService.Infrastructure.Persistence;
using Dumble.RecommendationService.Infrastructure.Recombee;
using Dumble.SharedKernel.Contracts;
using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using StackExchange.Redis;

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
        AddMessaging(services, configuration);
        AddServing(services, configuration);

        return services;
    }

    private static void AddServing(IServiceCollection services, IConfiguration configuration)
    {
        // Redis holds the rebuildable read-side projections + the explore cache (design D9).
        var redisConnection = configuration.GetConnectionString("Redis") ?? "localhost:6379";
        services.AddSingleton<IConnectionMultiplexer>(ConnectionMultiplexer.Connect(redisConnection));
        services.AddSingleton<IRecentPostsStore, RedisRecentPostsStore>();
        services.AddSingleton<IExploreFeedCache, RedisExploreFeedCache>();

        // Hydration: ranked ids -> renderable posts from PostService (forwarding the user JWT).
        services.AddHttpClient<IPostHydrator, PostServiceClient>(client =>
        {
            client.BaseAddress = new Uri(configuration["Services:PostService"] ?? "http://localhost:5134");
            client.Timeout = TimeSpan.FromSeconds(10);
        });
    }

    private static void AddMessaging(IServiceCollection services, IConfiguration configuration)
    {
        // Channel 2: consume existing domain events off the bus. Each consumer gets its own
        // queue via ConfigureEndpoints, so subscribing takes no messages from other services.
        services.AddMassTransit(x =>
        {
            x.AddConsumer<PostReactedConsumer>();
            x.AddConsumer<ReactionRemovedConsumer>();
            x.AddConsumer<CommentCreatedConsumer>();
            x.AddConsumer<PostCreatedConsumer>();
            x.AddConsumer<PostUpdatedConsumer>();
            x.AddConsumer<PostDeletedConsumer>();

            x.UsingRabbitMq((context, cfg) =>
            {
                cfg.Host(configuration["RabbitMQ:Host"] ?? "localhost", "/", h =>
                {
                    h.Username(configuration["RabbitMQ:Username"] ?? "guest");
                    h.Password(configuration["RabbitMQ:Password"] ?? "guest");
                });

                cfg.ConfigureEndpoints(context);
            });
        });
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
            services.AddHostedService<RecombeeSchemaInitializer>();
        }
        else
        {
            // No credentials — register a no-op client and skip the flush worker so the
            // service still boots locally (matches SocialService's optional-dependency style).
            services.AddSingleton<IRecombeeClient, DisabledRecombeeClient>();
        }
    }
}
