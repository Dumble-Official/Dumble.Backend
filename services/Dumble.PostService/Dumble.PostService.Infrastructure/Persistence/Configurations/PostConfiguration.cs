using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class PostConfiguration : IEntityTypeConfiguration<Post>
{
    public void Configure(EntityTypeBuilder<Post> builder)
    {
        builder.ToTable("posts");
        builder.HasKey(p => p.Id);

        builder.Property(p => p.AuthorId).IsRequired().HasMaxLength(100);
        builder.Property(p => p.AuthorDisplayName).IsRequired().HasMaxLength(200);
        builder.Property(p => p.AuthorProfileImage).HasMaxLength(500);
        builder.Property(p => p.Content).HasMaxLength(5000);
        builder.Property(p => p.GymId).HasMaxLength(100);

        builder.HasIndex(p => p.AuthorId).HasDatabaseName("ix_posts_author_id");
        builder.HasIndex(p => p.GymId).HasDatabaseName("ix_posts_gym_id");
        builder.HasIndex(p => p.CreatedAt).IsDescending().HasDatabaseName("ix_posts_created_at_desc");

        builder.HasMany(p => p.Images)
            .WithOne(i => i.Post)
            .HasForeignKey(i => i.PostId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasMany(p => p.PostHashtags)
            .WithOne(ph => ph.Post)
            .HasForeignKey(ph => ph.PostId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasMany(p => p.Reactions)
            .WithOne(r => r.Post)
            .HasForeignKey(r => r.PostId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasMany(p => p.Comments)
            .WithOne(c => c.Post)
            .HasForeignKey(c => c.PostId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}
