using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.SocialService.Domain.Entities;

namespace Dumble.SocialService.Infrastructure.Persistence.Configurations;

public class UserBehaviorConfiguration : IEntityTypeConfiguration<UserBehavior>
{
    public void Configure(EntityTypeBuilder<UserBehavior> builder)
    {
        builder.HasKey(b => b.Id);

        builder.Property(b => b.UserId).IsRequired().HasMaxLength(128);
        builder.Property(b => b.PostId).IsRequired().HasMaxLength(128);
        builder.Property(b => b.EventType).IsRequired().HasConversion<string>().HasMaxLength(50);
        builder.Property(b => b.EventData).HasMaxLength(1024);

        builder.HasIndex(b => new { b.UserId, b.CreatedAt });
        builder.HasIndex(b => new { b.UserId, b.PostId });
    }
}
