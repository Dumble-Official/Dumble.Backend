namespace Dumble.SocialService.Domain.Entities;

public class Follow
{
    public Guid Id { get; set; }
    public string FollowerId { get; set; } = null!;
    public string FolloweeId { get; set; } = null!;
    public string FolloweeType { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
}
