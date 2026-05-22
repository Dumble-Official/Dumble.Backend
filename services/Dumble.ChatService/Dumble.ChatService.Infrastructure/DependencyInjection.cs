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
        var mongoConnectionString = configuration.GetConnectionString("MongoDb");
        if (string.IsNullOrEmpty(mongoConnectionString))
            mongoConnectionString = "mongodb://dumble_chat_user:chatpass123@[::1]:27017/dumble_chat?authSource=dumble_chat";
        var databaseName = configuration["MongoDb:DatabaseName"];
        if (string.IsNullOrEmpty(databaseName))
            databaseName = "dumble_chat";

        services.AddSingleton<IMongoClient>(new MongoClient(mongoConnectionString));
        services.AddSingleton(sp => new MongoDbContext(sp.GetRequiredService<IMongoClient>(), databaseName));

        // Repositories
        services.AddScoped<IConversationRepository, ConversationRepository>();
        services.AddScoped<IMessageRepository, MessageRepository>();

        // Redis
        var redisConnection = configuration.GetConnectionString("Redis");
        if (string.IsNullOrEmpty(redisConnection))
            redisConnection = "localhost:6379";
        services.AddSingleton<IConnectionMultiplexer>(ConnectionMultiplexer.Connect(redisConnection));
        services.AddScoped<IPresenceService, RedisPresenceService>();

        // MassTransit + RabbitMQ (publish-only for ChatService)
        services.AddMassTransit(x =>
        {
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
            });
        });

        return services;
    }
}
