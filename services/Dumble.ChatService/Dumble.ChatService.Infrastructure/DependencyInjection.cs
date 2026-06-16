using MassTransit;
using MongoDB.Driver;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using StackExchange.Redis;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Infrastructure.Messaging.Consumers;
using Dumble.ChatService.Infrastructure.Persistence;
using Dumble.ChatService.Infrastructure.Persistence.Repositories;
using Dumble.ChatService.Infrastructure.Presence;

namespace Dumble.ChatService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // MongoDB
        var mongoConnectionString = configuration.GetConnectionString("MongoDb")
            ?? throw new InvalidOperationException("ConnectionStrings:MongoDb is required");
        var databaseName = configuration["MongoDb:DatabaseName"];
        if (string.IsNullOrEmpty(databaseName))
            databaseName = "dumble_chat";

        services.AddSingleton<IMongoClient>(new MongoClient(mongoConnectionString));
        services.AddSingleton(sp => new MongoDbContext(sp.GetRequiredService<IMongoClient>(), databaseName));

        // Repositories
        services.AddScoped<IConversationRepository, ConversationRepository>();
        services.AddScoped<IMessageRepository, MessageRepository>();
        services.AddScoped<IBlockRepository, BlockRepository>();

        // Redis
        var redisConnection = configuration.GetConnectionString("Redis");
        if (string.IsNullOrEmpty(redisConnection))
            redisConnection = "localhost:6379";
        services.AddSingleton<IConnectionMultiplexer>(ConnectionMultiplexer.Connect(redisConnection));
        services.AddScoped<IPresenceService, RedisPresenceService>();

        // MassTransit + RabbitMQ (publish-only for ChatService)
        services.AddMassTransit(x =>
        {
            // Account deletion arrives from the Java Auth service as raw JSON on the shared
            // dumble.events topic exchange, so it is wired explicitly below and kept out of the
            // convention-based ConfigureEndpoints.
            x.AddConsumer<AccountDeletedConsumer>().ExcludeFromConfigureEndpoints();

            x.UsingRabbitMq((context, cfg) =>
            {
                var rabbitHost = configuration["RabbitMQ:Host"];
                if (string.IsNullOrEmpty(rabbitHost)) rabbitHost = "localhost";
                var rabbitUser = configuration["RabbitMQ:Username"];
                if (string.IsNullOrEmpty(rabbitUser)) rabbitUser = "guest";
                var rabbitPass = configuration["RabbitMQ:Password"];
                if (string.IsNullOrEmpty(rabbitPass)) rabbitPass = "guest";
                cfg.Host(rabbitHost, "/", h =>
                {
                    h.Username(rabbitUser);
                    h.Password(rabbitPass);
                });

                cfg.ConfigureEndpoints(context);

                cfg.ReceiveEndpoint("chat-service.account-deleted", e =>
                {
                    e.UseRawJsonDeserializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "account.deleted"; });
                    e.ConfigureConsumer<AccountDeletedConsumer>(context);
                });
            });
        });

        return services;
    }
}
