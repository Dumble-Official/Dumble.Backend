using Dumble.PostService.Domain.Enums;

namespace Dumble.PostService.Domain.Entities;

public class Post
{
    public Guid Id { get; set; }
    public string AuthorId { get; set; } = string.Empty;
    public string AuthorDisplayName { get; set; } = string.Empty;
    public string? AuthorProfileImage { get; set; }
    public AuthorType AuthorType { get; set; }
    public string? Content { get; set; }
    public string? GymId { get; set; }
    public PostStatus Status { get; set; } = PostStatus.Active;
    public int ReactionsCount { get; set; }
    public int CommentsCount { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    public List<PostImage> Images { get; set; } = new();
    public List<PostHashtag> PostHashtags { get; set; } = new();
    public List<Reaction> Reactions { get; set; } = new();
    public List<Comment> Comments { get; set; } = new();
}
