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
        // Best-effort: a brief Mongo outage at startup shouldn't crash the
        // service. We log and continue — but we do NOT retry on first use,
        // so the TTL / unique indexes will be missing for the rest of this
        // process lifetime if this catch fires. Operator action required:
        // restart the service once Mongo is healthy. (CreateOne is
        // idempotent on subsequent boots; no risk of duplicate index.)
        try
        {
            ConfigureIndexes();
        }
        catch (MongoException ex)
        {
            _logger?.LogWarning(ex,
                "MongoDB index creation failed at startup; collections will run WITHOUT their TTL / unique indexes for this process. Restart the service once Mongo is reachable to register them.");
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
