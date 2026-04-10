using CloudinaryDotNet;
using CloudinaryDotNet.Actions;
using Dumble.PostService.Application.Contracts;

namespace Dumble.PostService.Infrastructure.Blobs;

public class CloudinaryFileService : IFileService
{
    private readonly Cloudinary _cloudinary;

    public CloudinaryFileService(Cloudinary cloudinary)
    {
        _cloudinary = cloudinary;
    }

    public async Task<(string Url, string PublicId)> UploadAsync(
        Stream stream, string fileName, string contentType, CancellationToken ct)
    {
        var uploadParams = new ImageUploadParams
        {
            File = new FileDescription(fileName, stream),
            Folder = "dumble/posts"
        };

        var result = await _cloudinary.UploadAsync(uploadParams, ct);

        if (result.Error is not null)
            throw new InvalidOperationException($"Cloudinary upload failed: {result.Error.Message}");

        return (result.SecureUrl.ToString(), result.PublicId);
    }

    public async Task<bool> DeleteAsync(string publicId, CancellationToken ct)
    {
        var deleteParams = new DeletionParams(publicId);
        var result = await _cloudinary.DestroyAsync(deleteParams);
        return result.Result == "ok";
    }
}
