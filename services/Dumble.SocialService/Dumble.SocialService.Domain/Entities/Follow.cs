namespace Dumble.SocialService.Domain.Entities;

public class Follow
{
    public Guid Id { get; set; }
    public string FollowerId { get; set; } = null!;
    public string FollowerName { get; set; } = string.Empty;
    public string? FollowerImage { get; set; }
    public string FolloweeId { get; set; } = null!;
    public string FolloweeType { get; set; } = null!;
    public DateTime CreatedAt { get; set; }
}
