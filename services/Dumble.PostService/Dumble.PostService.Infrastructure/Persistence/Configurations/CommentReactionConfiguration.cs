using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class CommentReactionConfiguration : IEntityTypeConfiguration<CommentReaction>
{
    public void Configure(EntityTypeBuilder<CommentReaction> builder)
    {
        builder.ToTable("comment_reactions");
        builder.HasKey(cr => cr.Id);

        builder.Property(cr => cr.UserId).IsRequired().HasMaxLength(100);
        builder.Property(cr => cr.Type).HasConversion<string>().HasMaxLength(50);

        builder.HasIndex(cr => new { cr.CommentId, cr.UserId })
            .IsUnique()
            .HasDatabaseName("ix_comment_reactions_comment_id_user_id");
    }
}
