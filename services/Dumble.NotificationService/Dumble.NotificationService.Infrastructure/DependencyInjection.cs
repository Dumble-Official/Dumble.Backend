using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using MassTransit;
using MongoDB.Driver;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Infrastructure.Messaging.Consumers;
using Dumble.NotificationService.Infrastructure.Persistence;
using Dumble.NotificationService.Infrastructure.Persistence.Repositories;
using Dumble.NotificationService.Infrastructure.Push;

namespace Dumble.NotificationService.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // MongoDB
        var connectionString = configuration.GetConnectionString("MongoDb") ?? "mongodb://admin:admin123@localhost:27017";
        var databaseName = configuration["MongoDb:DatabaseName"] ?? "dumble_notifications";

        services.AddSingleton<IMongoClient>(new MongoClient(connectionString));
        services.AddSingleton(sp => new MongoDbContext(sp.GetRequiredService<IMongoClient>(), databaseName));

        // Repositories
        services.AddScoped<INotificationRepository, NotificationRepository>();
        services.AddScoped<INotificationPreferenceRepository, NotificationPreferenceRepository>();
        services.AddScoped<IDeviceTokenRepository, DeviceTokenRepository>();

        // Firebase
        var firebaseCredPath = configuration["Firebase:CredentialsPath"];
        if (!string.IsNullOrEmpty(firebaseCredPath) && FirebaseApp.DefaultInstance is null)
        {
            FirebaseApp.Create(new AppOptions
            {
                Credential = GoogleCredential.FromFile(firebaseCredPath)
            });
        }
        services.AddScoped<IPushNotificationService, FirebasePushNotificationService>();

        // MassTransit + RabbitMQ
        services.AddMassTransit(x =>
        {
            x.AddConsumer<PostReactedConsumer>();
            x.AddConsumer<CommentCreatedConsumer>();
            x.AddConsumer<UserFollowedConsumer>();
            x.AddConsumer<MessageSentConsumer>();

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
