using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.ChatService.Domain.Models;

public class Conversation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;
    public string Type { get; set; } = null!;           // "Direct" or "Group"
    public string? Name { get; set; }                    // Only for groups
    public string? ImageUrl { get; set; }                // Group avatar
    public string CreatedBy { get; set; } = null!;       // UserId
    public List<Participant> Participants { get; set; } = new();
    public LastMessageInfo? LastMessage { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

public class Participant
{
    public string UserId { get; set; } = null!;
    public string DisplayName { get; set; } = null!;
    public string? ProfileImage { get; set; }
    public string Role { get; set; } = "Member";         // "Admin" or "Member"
    public DateTime JoinedAt { get; set; }
    public string? LastReadMessageId { get; set; }
}

public class LastMessageInfo
{
    public string MessageId { get; set; } = null!;
    public string SenderId { get; set; } = null!;
    public string SenderName { get; set; } = null!;
    public string Content { get; set; } = null!;
    public DateTime SentAt { get; set; }
}
