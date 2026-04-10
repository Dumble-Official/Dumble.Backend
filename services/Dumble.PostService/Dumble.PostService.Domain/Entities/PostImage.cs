namespace Dumble.PostService.Domain.Entities;

public class PostImage
{
    public Guid Id { get; set; }
    public Guid PostId { get; set; }
    public string ImageUrl { get; set; } = string.Empty;
    public string PublicId { get; set; } = string.Empty;
    public int Order { get; set; }

    public Post Post { get; set; } = null!;
}
