namespace Dumble.PostService.Application.Contracts;

public interface IFileService
{
    Task<(string Url, string PublicId)> UploadAsync(Stream stream, string fileName, string contentType, CancellationToken ct = default);
    Task<bool> DeleteAsync(string publicId, CancellationToken ct = default);
}
