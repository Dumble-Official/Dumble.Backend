using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.NotificationService.Domain.Models;

public class DeviceToken
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;

    public string UserId { get; set; } = null!;
    public string Token { get; set; } = null!;
    public string Platform { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
