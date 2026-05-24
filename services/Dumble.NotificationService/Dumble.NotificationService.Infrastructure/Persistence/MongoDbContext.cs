using MongoDB.Driver;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence;

public class MongoDbContext
{
    private readonly IMongoDatabase _database;

    public MongoDbContext(IMongoClient client, string databaseName)
    {
        _database = client.GetDatabase(databaseName);
        ConfigureIndexes();
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
            Builders<DedupEventEntry>.IndexKeys.Ascending(e => e.ConsumedAt),
            new CreateIndexOptions { ExpireAfter = TimeSpan.FromDays(7) }));
    }
}
