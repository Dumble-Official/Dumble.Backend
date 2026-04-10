using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace Dumble.NotificationService.Domain.Models;

public class NotificationPreference
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = null!;

    public string UserId { get; set; } = null!;
    public Dictionary<string, ChannelPreference> Preferences { get; set; } = new();
}

public class ChannelPreference
{
    public bool Push { get; set; } = true;
    public bool InApp { get; set; } = true;
}
