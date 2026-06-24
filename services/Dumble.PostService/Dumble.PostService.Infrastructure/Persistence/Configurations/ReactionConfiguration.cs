using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class ReactionConfiguration : IEntityTypeConfiguration<Reaction>
{
    public void Configure(EntityTypeBuilder<Reaction> builder)
    {
        builder.ToTable("reactions");
        builder.HasKey(r => r.Id);

        builder.Property(r => r.UserId).IsRequired().HasMaxLength(100);
        builder.Property(r => r.DisplayName).IsRequired().HasMaxLength(200);
        builder.Property(r => r.ProfileImage).HasMaxLength(500);
        builder.Property(r => r.Type).HasConversion<string>().HasMaxLength(50);

        builder.HasIndex(r => new { r.PostId, r.UserId })
            .IsUnique()
            .HasDatabaseName("ix_reactions_post_id_user_id");
    }
}
