using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using FluentValidation;

namespace Dumble.BundleManagementService.API.Validation;

public sealed class CreateBundleRequestValidator : AbstractValidator<CreateBundleRequest>
{
    private const int MaxImages = 10;
    private const long PerImageBytesCap = 5L * 1024 * 1024;     // 5 MB
    private static readonly string[] AllowedStatuses = ["Draft", "Published", "Archived"];

    public CreateBundleRequestValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(200);
        RuleFor(x => x.Description).NotEmpty().MaximumLength(2000);
        RuleFor(x => x.Price).GreaterThanOrEqualTo(0);
        RuleFor(x => x.Status)
            .NotEmpty()
            .Must(s => AllowedStatuses.Contains(s, StringComparer.OrdinalIgnoreCase))
            .WithMessage("Status must be one of: Draft, Published, Archived");
        RuleFor(x => x.ExpiresOn).GreaterThan(DateTime.UtcNow);
        RuleFor(x => x.CategoryId).NotEqual(Guid.Empty);

        // Upload caps — without these a client could submit 1000 large or non-image
        // files and the handler would happily stream every one to Cloudinary. Matches
        // the per-file pattern PR #10 added on PostService.
        RuleFor(x => x.Images)
            .Must(imgs => imgs == null || imgs.Count <= MaxImages)
            .WithMessage($"At most {MaxImages} images per bundle")
            .Must(imgs => imgs == null || imgs.All(f => f.Length > 0 && f.Length <= PerImageBytesCap))
            .WithMessage($"Each image must be 1 byte – {PerImageBytesCap / 1024 / 1024} MB")
            .Must(imgs => imgs == null || imgs.All(f =>
                !string.IsNullOrEmpty(f.ContentType) &&
                f.ContentType.StartsWith("image/", StringComparison.OrdinalIgnoreCase)))
            .WithMessage("Only image content types are allowed");
    }
}
