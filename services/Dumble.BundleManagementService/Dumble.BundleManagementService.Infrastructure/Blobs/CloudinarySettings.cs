namespace Dumble.BundleManagementService.Infrastructure.Blobs;

public sealed class CloudinarySettings
{
    public string CloudName { get; set; } = default!;
    public string ApiKey { get; set; } = default!;
    public string ApiSecret { get; set; } = default!;
}