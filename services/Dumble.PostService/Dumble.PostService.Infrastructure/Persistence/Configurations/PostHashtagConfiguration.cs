using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class PostHashtagConfiguration : IEntityTypeConfiguration<PostHashtag>
{
    public void Configure(EntityTypeBuilder<PostHashtag> builder)
    {
        builder.ToTable("post_hashtags");
        builder.HasKey(ph => new { ph.PostId, ph.HashtagId });

        builder.HasOne(ph => ph.Hashtag)
            .WithMany(h => h.PostHashtags)
            .HasForeignKey(ph => ph.HashtagId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasIndex(ph => ph.HashtagId).HasDatabaseName("ix_post_hashtags_hashtag_id");
    }
}
