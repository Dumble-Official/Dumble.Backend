using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using MassTransit;
using MongoDB.Driver;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Infrastructure.Configuration;
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
        var connectionString = configuration.GetConnectionString("MongoDb") ?? throw new InvalidOperationException("ConnectionStrings:MongoDb is required");
        var databaseName = configuration["MongoDb:DatabaseName"] ?? "dumble_notifications";

        services.AddSingleton<IMongoClient>(new MongoClient(connectionString));
        services.AddSingleton(sp => new MongoDbContext(
            sp.GetRequiredService<IMongoClient>(),
            databaseName,
            sp.GetService<ILogger<MongoDbContext>>()));

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

        // Dedup + notification delivery.
        // IDedupEventStore is intentionally singleton: it holds an IMongoCollection
        // handle, which is documented thread-safe by the MongoDB C# driver and
        // safe to share across requests. Don't lower this to Scoped without
        // changing the implementation — the surrounding NotificationDeliveryService
        // is scoped because it composes per-request repositories.
        services.AddSingleton<IDedupEventStore, DedupEventStore>();
        services.AddScoped<INotificationDeliveryService, NotificationDeliveryService>();
        services.Configure<NotificationSettings>(configuration.GetSection(NotificationSettings.SectionName));

        // MassTransit + RabbitMQ
        services.AddMassTransit(x =>
        {
            // .NET service consumers (MassTransit-typed exchanges)
            x.AddConsumer<PostReactedConsumer>();
            x.AddConsumer<CommentReactedConsumer>();
            x.AddConsumer<CommentCreatedConsumer>();
            x.AddConsumer<UserFollowedConsumer>();
            x.AddConsumer<MessageSentConsumer>();

            // Account deletion (right-to-be-forgotten) — Java Auth raw-JSON event.
            x.AddConsumer<AccountDeletedConsumer>();

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

                // .NET service consumers (MassTransit-typed exchanges) — explicit endpoints
                // to prevent double-registration with subscription consumers on the topic exchange
                cfg.ReceiveEndpoint("PostReacted", e =>
                {
                    e.ConfigureConsumer<PostReactedConsumer>(context);
                });
                cfg.ReceiveEndpoint("CommentReacted", e =>
                {
                    e.ConfigureConsumer<CommentReactedConsumer>(context);
                });
                cfg.ReceiveEndpoint("CommentCreated", e =>
                {
                    e.ConfigureConsumer<CommentCreatedConsumer>(context);
                });
                cfg.ReceiveEndpoint("UserFollowed", e =>
                {
                    e.ConfigureConsumer<UserFollowedConsumer>(context);
                });
                cfg.ReceiveEndpoint("MessageSent", e =>
                {
                    e.ConfigureConsumer<MessageSentConsumer>(context);
                });

                // Account deletion (right-to-be-forgotten) from the Java Auth service.
                cfg.ReceiveEndpoint("notification-service.account-deleted", e =>
                {
                    e.UseRawJsonDeserializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "account.deleted"; });
                    e.ConfigureConsumer<AccountDeletedConsumer>(context);
                });

                // Java Subscription service consumers (dumble.events topic exchange)
                // Each gets its own endpoint bound with a specific routing key so
                // RabbitMQ's topic exchange delivers only matching events — necessary
                // because Java publishes raw JSON without a MassTransit envelope, and
                // UseRawJsonSerializer on a shared endpoint would dispatch every message
                // to all 16 consumers (all record types deserialize from any JSON).
                cfg.ReceiveEndpoint("notification-service.bundle-activated", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.bundle.activated"; });
                    e.ConfigureConsumer<BundleActivatedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.bundle-expired", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.bundle.expired"; });
                    e.ConfigureConsumer<BundleExpiredConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.chargeback-processed", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.chargeback.processed"; });
                    e.ConfigureConsumer<ChargebackProcessedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.payment-failed", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.payment.failed"; });
                    e.ConfigureConsumer<PaymentFailedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.payment-failed-final", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.payment.failed-final"; });
                    e.ConfigureConsumer<PaymentFailedFinalConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.plan-changed", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.plan.changed"; });
                    e.ConfigureConsumer<PlanChangedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.platform-activated", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.platform.activated"; });
                    e.ConfigureConsumer<PlatformActivatedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.platform-expired", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.platform.expired"; });
                    e.ConfigureConsumer<PlatformExpiredConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.receipt-issued", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.receipt.issued"; });
                    e.ConfigureConsumer<ReceiptIssuedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.refund-issued", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.refund.issued"; });
                    e.ConfigureConsumer<RefundIssuedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.renewal-prompt", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.*.renewal-prompt"; });
                    e.ConfigureConsumer<RenewalPromptConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.seller-banned", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.seller.banned"; });
                    e.ConfigureConsumer<SellerBannedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.seller-closed", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.seller.closed"; });
                    e.ConfigureConsumer<SellerClosedConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.seller-frozen", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.seller.frozen"; });
                    e.ConfigureConsumer<SellerFrozenConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.seller-unfrozen", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.seller.unfrozen"; });
                    e.ConfigureConsumer<SellerUnfrozenConsumer>(context);
                });
                cfg.ReceiveEndpoint("notification-service.seller-winding-down", e =>
                {
                    e.UseRawJsonSerializer();
                    e.UseMessageRetry(r => r.Interval(3, TimeSpan.FromSeconds(5)));
                    e.Bind("dumble.events", b => { b.ExchangeType = "topic"; b.Durable = true; b.RoutingKey = "subscription.seller.winding-down"; });
                    e.ConfigureConsumer<SellerWindingDownConsumer>(context);
                });
            });
        });

        return services;
    }
}
