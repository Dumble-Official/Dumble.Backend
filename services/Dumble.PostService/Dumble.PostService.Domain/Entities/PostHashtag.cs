namespace Dumble.PostService.Domain.Entities;

public class PostHashtag
{
    public Guid PostId { get; set; }
    public Guid HashtagId { get; set; }

    public Post Post { get; set; } = null!;
    public Hashtag Hashtag { get; set; } = null!;
}
