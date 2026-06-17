using MassTransit;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Dumble.SharedKernel.Contracts;
using Dumble.SocialService.Application.Contracts;
using Dumble.SocialService.Infrastructure.Authentication;
using Dumble.SocialService.Infrastructure.ExternalServices;
using Dumble.SocialService.Infrastructure.Messaging.Consumers;
using Dumble.SocialService.Infrastructure.Persistence;
using Dumble.SocialService.Infrastructure.Persistence.Repositories;

namespace Dumble.SocialService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // PostgreSQL + EF Core — social owns the follow graph.
        services.AddDbContext<SocialDbContext>(options =>
            options.UseNpgsql(configuration.GetConnectionString("PostgreSql") ?? "Host=localhost;Port=5433;Database=dumble_social;Username=dumble;Password=dumble123")
                   .UseSnakeCaseNamingConvention());

        services.AddScoped<IFollowRepository, FollowRepository>();

        services.AddHttpContextAccessor();

        // Current user is read from validated JWT claims — no extra HTTP call.
        services.AddScoped<ILoggedInUserService, LoggedInUserService>();

        // Home feed is ranked by Recombee in the recommendation service; /api/social/feed proxies
        // to GET /api/feed/home there (forwarding the caller's token). Social no longer ranks feeds.
        services.AddHttpClient<IHomeFeedClient, HomeFeedClient>(client =>
        {
            client.BaseAddress = new Uri(configuration["Services:RecommendationApi"] ?? "http://localhost:5024");
            client.Timeout = TimeSpan.FromSeconds(10);
        });

        // MassTransit + RabbitMQ — social publishes follow events; its only consumer is the
        // right-to-be-forgotten cleanup (the old feed-ranking consumers were retired with the
        // move to Recombee).
        services.AddMassTransit(x =>
        {
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
