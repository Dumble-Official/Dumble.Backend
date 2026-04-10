using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class CommentConfiguration : IEntityTypeConfiguration<Comment>
{
    public void Configure(EntityTypeBuilder<Comment> builder)
    {
        builder.ToTable("comments");
        builder.HasKey(c => c.Id);

        builder.Property(c => c.AuthorId).IsRequired().HasMaxLength(100);
        builder.Property(c => c.AuthorDisplayName).IsRequired().HasMaxLength(200);
        builder.Property(c => c.AuthorProfileImage).HasMaxLength(500);
        builder.Property(c => c.Content).IsRequired().HasMaxLength(2000);
        builder.Property(c => c.Status).HasConversion<string>().HasMaxLength(50);

        builder.HasOne(c => c.ParentComment)
            .WithMany(c => c.Replies)
            .HasForeignKey(c => c.ParentCommentId)
            .OnDelete(DeleteBehavior.Restrict);

        builder.HasMany(c => c.CommentReactions)
            .WithOne(cr => cr.Comment)
            .HasForeignKey(cr => cr.CommentId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasIndex(c => new { c.PostId, c.CreatedAt })
            .HasDatabaseName("ix_comments_post_id_created_at");

        builder.HasIndex(c => c.ParentCommentId)
            .HasDatabaseName("ix_comments_parent_comment_id");
    }
}
