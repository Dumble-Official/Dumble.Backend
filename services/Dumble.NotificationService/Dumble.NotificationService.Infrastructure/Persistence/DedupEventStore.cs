using MongoDB.Driver;
using Dumble.NotificationService.Application.Contracts;
using Dumble.NotificationService.Domain.Models;

namespace Dumble.NotificationService.Infrastructure.Persistence;

public class DedupEventStore : IDedupEventStore
{
    private static readonly TimeSpan Retention = TimeSpan.FromDays(7);
    private readonly IMongoCollection<DedupEventEntry> _collection;

    public DedupEventStore(MongoDbContext context)
    {
        _collection = context.DedupEvents;
    }

    public async Task<bool> TryClaimAsync(string messageId, string consumerType, CancellationToken ct)
    {
        try
        {
            var now = DateTime.UtcNow;
            await _collection.InsertOneAsync(
                new DedupEventEntry
                {
                    MessageId = messageId,
                    ConsumerType = consumerType,
                    ConsumedAt = now,
                    ExpiresAt = now.Add(Retention)
                },
                new InsertOneOptions(), ct);
            return true;
        }
        catch (MongoWriteException ex) when (ex.WriteError?.Category == ServerErrorCategory.DuplicateKey)
        {
            return false;
        }
    }
}
