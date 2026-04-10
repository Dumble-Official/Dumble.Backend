using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Dumble.PostService.Domain.Entities;

namespace Dumble.PostService.Infrastructure.Persistence.Configurations;

public class PostImageConfiguration : IEntityTypeConfiguration<PostImage>
{
    public void Configure(EntityTypeBuilder<PostImage> builder)
    {
        builder.ToTable("post_images");
        builder.HasKey(pi => pi.Id);

        builder.Property(pi => pi.ImageUrl).IsRequired().HasMaxLength(500);
        builder.Property(pi => pi.PublicId).IsRequired().HasMaxLength(200);
    }
}
