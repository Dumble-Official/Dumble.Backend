using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.ChatService.Domain.Models;

public class Message
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;
    public string ConversationId { get; set; } = null!;
    public string SenderId { get; set; } = null!;
    public string SenderName { get; set; } = null!;
    public string? SenderProfileImage { get; set; }
    public string Content { get; set; } = null!;
    public string MessageType { get; set; } = "Text";    // "Text", "Image", "System"
    public string? ImageUrl { get; set; }
    public string? ReplyToMessageId { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime CreatedAt { get; set; }
}
