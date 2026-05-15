namespace Dumble.BundleManagementService.Application.Contracts;

public sealed record UploadedImage(Stream Content, string FileName, string ContentType);
