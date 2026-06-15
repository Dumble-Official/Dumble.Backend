using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.ChatService.Domain.Models;

/// <summary>One user blocking another. Blocking is one-directional but enforced both ways for chat.</summary>
public class UserBlock
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;

    public string BlockerId { get; set; } = null!;
    public string BlockedId { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
}
