using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Domain.Entities;

public class Reaction
{
    public Guid Id { get; set; }
    public Guid PostId { get; set; }
    public string UserId { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string? ProfileImage { get; set; }
    public ReactionType Type { get; set; }
    public DateTime CreatedAt { get; set; }

    public Post Post { get; set; } = null!;
}
