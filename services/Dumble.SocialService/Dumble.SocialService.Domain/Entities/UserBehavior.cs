using Dumble.SocialService.Domain.Enums;

namespace Dumble.SocialService.Domain.Entities;

public class UserBehavior
{
    public Guid Id { get; set; }
    public string UserId { get; set; } = null!;
    public string PostId { get; set; } = null!;
    public BehaviorEventType EventType { get; set; }
    public string? EventData { get; set; }
    public DateTime CreatedAt { get; set; }
}
