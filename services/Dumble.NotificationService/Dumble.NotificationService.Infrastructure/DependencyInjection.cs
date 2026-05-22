using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using MassTransit;
using MongoDB.Driver;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Infrastructure.Messaging.Consumers;
using Dumble.NotificationService.Infrastructure.Messaging.Consumers.Subscription;
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
            // .NET service consumers (MassTransit-typed exchanges)
            x.AddConsumer<PostReactedConsumer>();
            x.AddConsumer<CommentCreatedConsumer>();
            x.AddConsumer<UserFollowedConsumer>();
            x.AddConsumer<MessageSentConsumer>();

            // Java Subscription service consumers (dumble.events topic exchange)
            x.AddConsumer<BundleActivatedConsumer>();
            x.AddConsumer<BundleExpiredConsumer>();
            x.AddConsumer<ChargebackProcessedConsumer>();
            x.AddConsumer<PaymentFailedConsumer>();
            x.AddConsumer<PaymentFailedFinalConsumer>();
            x.AddConsumer<PlanChangedConsumer>();
            x.AddConsumer<PlatformActivatedConsumer>();
            x.AddConsumer<PlatformExpiredConsumer>();
            x.AddConsumer<ReceiptIssuedConsumer>();
            x.AddConsumer<RefundIssuedConsumer>();
            x.AddConsumer<RenewalPromptConsumer>();
            x.AddConsumer<SellerBannedConsumer>();
            x.AddConsumer<SellerClosedConsumer>();
            x.AddConsumer<SellerFrozenConsumer>();
            x.AddConsumer<SellerUnfrozenConsumer>();
            x.AddConsumer<SellerWindingDownConsumer>();

            x.UsingRabbitMq((context, cfg) =>
            {
                cfg.Host(configuration["RabbitMQ:Host"] ?? "localhost", "/", h =>
                {
                    h.Username(configuration["RabbitMQ:Username"] ?? "guest");
                    h.Password(configuration["RabbitMQ:Password"] ?? "guest");
                });

                // Auto-configure endpoints for .NET MassTransit-typed exchanges
                cfg.ConfigureEndpoints(context);

                // Explicit endpoint for Java-side dumble.events topic exchange
                // Subscription publishes raw JSON (no MassTransit envelope) with
                // routing keys like subscription.seller.frozen, subscription.bundle.activated, etc.
                cfg.ReceiveEndpoint("notification-service.subscription-events", e =>
                {
                    e.UseRawJsonSerializer();
                    e.Bind("dumble.events", b =>
                    {
                        b.ExchangeType = "topic";
                        b.Durable = true;
                        b.RoutingKey = "subscription.#";
                    });
                    e.ConfigureConsumer<BundleActivatedConsumer>(context);
                    e.ConfigureConsumer<BundleExpiredConsumer>(context);
                    e.ConfigureConsumer<ChargebackProcessedConsumer>(context);
                    e.ConfigureConsumer<PaymentFailedConsumer>(context);
                    e.ConfigureConsumer<PaymentFailedFinalConsumer>(context);
                    e.ConfigureConsumer<PlanChangedConsumer>(context);
                    e.ConfigureConsumer<PlatformActivatedConsumer>(context);
                    e.ConfigureConsumer<PlatformExpiredConsumer>(context);
                    e.ConfigureConsumer<ReceiptIssuedConsumer>(context);
                    e.ConfigureConsumer<RefundIssuedConsumer>(context);
                    e.ConfigureConsumer<RenewalPromptConsumer>(context);
                    e.ConfigureConsumer<SellerBannedConsumer>(context);
                    e.ConfigureConsumer<SellerClosedConsumer>(context);
                    e.ConfigureConsumer<SellerFrozenConsumer>(context);
                    e.ConfigureConsumer<SellerUnfrozenConsumer>(context);
                    e.ConfigureConsumer<SellerWindingDownConsumer>(context);
                });
            });
        });

        return services;
    }
}
