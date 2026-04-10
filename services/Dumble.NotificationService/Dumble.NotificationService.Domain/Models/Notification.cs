using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.NotificationService.Domain.Models;

public class Notification
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;

    public string RecipientId { get; set; } = null!;
    public string Type { get; set; } = null!;
    public string Title { get; set; } = null!;
    public string Body { get; set; } = null!;
    public Dictionary<string, string> Data { get; set; } = new();
    public bool IsRead { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? ExpiresAt { get; set; }
}
