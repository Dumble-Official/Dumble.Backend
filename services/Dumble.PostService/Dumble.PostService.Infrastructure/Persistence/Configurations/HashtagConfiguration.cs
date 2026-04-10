using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class HashtagConfiguration : IEntityTypeConfiguration<Hashtag>
{
    public void Configure(EntityTypeBuilder<Hashtag> builder)
    {
        builder.ToTable("hashtags");
        builder.HasKey(h => h.Id);

        builder.Property(h => h.Name).IsRequired().HasMaxLength(100);

        builder.HasIndex(h => h.Name).IsUnique().HasDatabaseName("ix_hashtags_name");
        builder.HasIndex(h => h.UsageCount).IsDescending().HasDatabaseName("ix_hashtags_usage_count_desc");
    }
}
