namespace Dumble.BundleManagementService.Application.Contracts;

public interface IFileService
{
    Task<string> UploadAsync(Stream fileStream, string fileName, string contentType);
    
    Task<Stream> DownloadAsync(string filePath);
    
    Task<bool> DeleteAsync(string filePath);

    Task<bool> ExistsAsync(string filePath);
}