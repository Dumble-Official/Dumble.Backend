using Microsoft.Extensions.Logging;
using MongoDB.Driver;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence;

public class MongoDbContext
{
    private readonly IMongoDatabase _database;
    private readonly ILogger<MongoDbContext>? _logger;

    public MongoDbContext(IMongoClient client, string databaseName, ILogger<MongoDbContext>? logger = null)
    {
        _database = client.GetDatabase(databaseName);
        _logger = logger;
        // Index creation runs at startup but tolerates transient Mongo
        // unavailability — a brief broker hiccup shouldn't crash the service.
        // CreateOne is idempotent so retrying on first real use is safe.
        try
        {
            ConfigureIndexes();
        }
        catch (MongoException ex)
        {
            _logger?.LogWarning(ex, "Index configuration failed at startup; collections will retry on first use");
        }
    }

    public IMongoCollection<Notification> Notifications => _database.GetCollection<Notification>("notifications");
    public IMongoCollection<NotificationPreference> NotificationPreferences => _database.GetCollection<NotificationPreference>("notification_preferences");
    public IMongoCollection<DeviceToken> DeviceTokens => _database.GetCollection<DeviceToken>("device_tokens");
    public IMongoCollection<DedupEventEntry> DedupEvents => _database.GetCollection<DedupEventEntry>("dedup_events");

    private void ConfigureIndexes()
    {
        Notifications.Indexes.CreateMany(new[]
        {
            new CreateIndexModel<Notification>(
                Builders<Notification>.IndexKeys.Ascending(n => n.RecipientId).Descending(n => n.CreatedAt)),
            new CreateIndexModel<Notification>(
                Builders<Notification>.IndexKeys.Ascending(n => n.RecipientId).Ascending(n => n.IsRead)),
            new CreateIndexModel<Notification>(
                Builders<Notification>.IndexKeys.Ascending(n => n.ExpiresAt),
                new CreateIndexOptions { ExpireAfter = TimeSpan.Zero })
        });

        NotificationPreferences.Indexes.CreateOne(new CreateIndexModel<NotificationPreference>(
            Builders<NotificationPreference>.IndexKeys.Ascending(p => p.UserId),
            new CreateIndexOptions { Unique = true }));

        DeviceTokens.Indexes.CreateOne(new CreateIndexModel<DeviceToken>(
            Builders<DeviceToken>.IndexKeys.Ascending(d => d.UserId)));

        DedupEvents.Indexes.CreateOne(new CreateIndexModel<DedupEventEntry>(
            Builders<DedupEventEntry>.IndexKeys.Ascending(e => e.ExpiresAt),
            new CreateIndexOptions { ExpireAfter = TimeSpan.Zero }));
    }
}
