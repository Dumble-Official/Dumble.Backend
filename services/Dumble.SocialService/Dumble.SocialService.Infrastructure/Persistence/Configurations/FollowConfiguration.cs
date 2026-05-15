using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.Persistence.Configurations;

public class FollowConfiguration : IEntityTypeConfiguration<Follow>
{
    public void Configure(EntityTypeBuilder<Follow> builder)
    {
        builder.HasKey(f => f.Id);

        builder.Property(f => f.FollowerId).IsRequired().HasMaxLength(128);
        builder.Property(f => f.FollowerName).HasMaxLength(200);
        builder.Property(f => f.FollowerImage).HasMaxLength(500);
        builder.Property(f => f.FolloweeId).IsRequired().HasMaxLength(128);
        builder.Property(f => f.FolloweeType).IsRequired().HasMaxLength(50);

        builder.HasIndex(f => new { f.FollowerId, f.FolloweeId }).IsUnique();
        builder.HasIndex(f => new { f.FolloweeId, f.CreatedAt });
        builder.HasIndex(f => new { f.FollowerId, f.CreatedAt });
    }
}
