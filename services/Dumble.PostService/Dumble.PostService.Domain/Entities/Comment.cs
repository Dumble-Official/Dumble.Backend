using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Domain.Entities;

public class Comment
{
    public Guid Id { get; set; }
    public Guid PostId { get; set; }
    public string AuthorId { get; set; } = string.Empty;
    public string AuthorDisplayName { get; set; } = string.Empty;
    public string? AuthorProfileImage { get; set; }
    public Guid? ParentCommentId { get; set; }
    public string Content { get; set; } = string.Empty;
    public CommentStatus Status { get; set; } = CommentStatus.Active;
    public int ReactionsCount { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    public Post Post { get; set; } = null!;
    public Comment? ParentComment { get; set; }
    public List<Comment> Replies { get; set; } = new();
    public List<CommentReaction> CommentReactions { get; set; } = new();
}
