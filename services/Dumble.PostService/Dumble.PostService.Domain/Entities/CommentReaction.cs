using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Domain.Entities;

public class CommentReaction
{
    public Guid Id { get; set; }
    public Guid CommentId { get; set; }
    public string UserId { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string? ProfileImage { get; set; }
    public ReactionType Type { get; set; }
    public DateTime CreatedAt { get; set; }

    public Comment Comment { get; set; } = null!;
}
