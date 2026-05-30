using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.NotificationService.Domain.Models;

public class DedupEventEntry
{
    [BsonId]
    public string Id => $"{MessageId}:{ConsumerType}";

    public string MessageId { get; set; } = null!;
    public string ConsumerType { get; set; } = null!;
    public DateTime ConsumedAt { get; set; }

    [BsonDefaultValue(null)]
    public DateTime? ExpiresAt { get; set; }
}
