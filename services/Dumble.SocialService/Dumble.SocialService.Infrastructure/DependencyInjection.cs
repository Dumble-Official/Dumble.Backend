using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using StackExchange.Redis;
using Dumble.SharedKernel.Contracts;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Infrastructure.Authentication;
using Dumble.SocialService.Infrastructure.Caching;
using Dumble.SocialService.Infrastructure.ExternalServices;
using Dumble.SocialService.Infrastructure.Messaging.Consumers;
using Dumble.SocialService.Infrastructure.Persistence;
using Dumble.SocialService.Infrastructure.Persistence.Repositories;

namespace Dumble.SocialService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // PostgreSQL + EF Core
        services.AddDbContext<SocialDbContext>(options =>
            options.UseNpgsql(configuration.GetConnectionString("PostgreSql") ?? "Host=localhost;Port=5433;Database=dumble_social;Username=dumble;Password=dumble123")
                   .UseSnakeCaseNamingConvention());

        // Repositories
        services.AddScoped<IFollowRepository, FollowRepository>();
        services.AddScoped<IUserBehaviorRepository, UserBehaviorRepository>();

        // Redis
        var redisConnection = configuration.GetConnectionString("Redis") ?? "localhost:6379";
        services.AddSingleton<IConnectionMultiplexer>(ConnectionMultiplexer.Connect(redisConnection));
        services.AddScoped<IFeedCacheService, RedisFeedCacheService>();

        services.AddHttpContextAccessor();

        // Current user is read from validated JWT claims — no extra HTTP call.
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        // PostService — fixed wrong default port (was 5020, Post listens on 5134).
        services.AddHttpClient<IPostServiceClient, PostServiceClient>(client =>
        {
            client.BaseAddress = new Uri(configuration["Services:PostService"] ?? "http://localhost:5134");
            client.Timeout = TimeSpan.FromSeconds(10);
        });

        // RankingApi is optional — when not configured, RankingServiceClient
        // detects the missing config and short-circuits to an empty result
        // (logging a warning) instead of silently swallowing connection refused.
        var rankingUrl = configuration["Services:RankingApi"];
        services.AddHttpClient<IRankingServiceClient, RankingServiceClient>(client =>
        {
            client.BaseAddress = new Uri(rankingUrl ?? "http://ranking-not-configured.invalid");
            client.Timeout = TimeSpan.FromSeconds(5);
        });

        // MassTransit + RabbitMQ
        services.AddMassTransit(x =>
        {
            x.AddConsumer<PostCreatedConsumer>();
            x.AddConsumer<PostDeletedConsumer>();
            x.AddConsumer<PostReactedConsumer>();
            x.AddConsumer<CommentCreatedConsumer>();

            // Account deletion arrives from the Java Auth service as raw JSON on the shared
            // dumble.events topic exchange (no MassTransit envelope), so it is wired explicitly
            // and excluded from the convention-based ConfigureEndpoints.
            x.AddConsumer<AccountDeletedConsumer>().ExcludeFromConfigureEndpoints();

            x.UsingRabbitMq((context, cfg) =>
            {
                cfg.Host(configuration["RabbitMQ:Host"] ?? "localhost", "/", h =>
                {
                    h.Username(configuration["RabbitMQ:Username"] ?? "guest");
                    h.Password(configuration["RabbitMQ:Password"] ?? "guest");
                });

                cfg.ConfigureEndpoints(context);

                cfg.ReceiveEndpoint("social-service.account-deleted", e =>
                {
                    // Deserializer-only raw JSON: keeps inbound parsing working without forcing
                    // this endpoint's own publishes to drop their envelope.
                    e.UseRawJsonDeserializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b =>
                    {
                        b.ExchangeType = "topic";
                        b.Durable = true;
                        b.RoutingKey = "account.deleted";
                    });
                    e.ConfigureConsumer<AccountDeletedConsumer>(context);
                });
            });
        });

        return services;
    }
}
