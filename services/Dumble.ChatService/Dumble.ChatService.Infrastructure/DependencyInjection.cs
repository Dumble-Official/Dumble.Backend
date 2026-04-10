using MassTransit;
using MongoDB.Driver;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using StackExchange.Redis;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Infrastructure.Persistence;
using Dumble.ChatService.Infrastructure.Persistence.Repositories;
using Dumble.ChatService.Infrastructure.Presence;

namespace Dumble.ChatService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // MongoDB
        var connectionString = configuration.GetConnectionString("MongoDb") ?? "mongodb://admin:admin123@localhost:27017";
        var databaseName = configuration["MongoDb:DatabaseName"] ?? "dumble_chat";

        services.AddSingleton<IMongoClient>(new MongoClient(connectionString));
        services.AddSingleton(sp => new MongoDbContext(sp.GetRequiredService<IMongoClient>(), databaseName));

        // Repositories
        services.AddScoped<IConversationRepository, ConversationRepository>();
        services.AddScoped<IMessageRepository, MessageRepository>();

        // Redis
        var redisConnection = configuration.GetConnectionString("Redis") ?? "localhost:6379";
        services.AddSingleton<IConnectionMultiplexer>(ConnectionMultiplexer.Connect(redisConnection));
        services.AddScoped<IPresenceService, RedisPresenceService>();

        // MassTransit + RabbitMQ (publish-only for ChatService)
        services.AddMassTransit(x =>
        {
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

        return services;
    }
}
