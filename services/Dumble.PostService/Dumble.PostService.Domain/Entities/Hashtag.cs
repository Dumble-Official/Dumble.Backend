namespace Dumble.PostService.Domain.Entities;

public class Hashtag
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public int UsageCount { get; set; }

    public List<PostHashtag> PostHashtags { get; set; } = new();
}
