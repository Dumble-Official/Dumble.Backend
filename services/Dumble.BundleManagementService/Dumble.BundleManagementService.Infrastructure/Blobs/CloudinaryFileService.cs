using System.Net.Http;
using CloudinaryDotNet;
using CloudinaryDotNet.Actions;
using Dumble.BundleManagementService.Application.Contracts;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Infrastructure.Blobs;

public sealed class CloudinaryFileService : IFileService
{
    private readonly Cloudinary _cloudinary;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly string? _folder;
    private readonly ILogger<CloudinaryFileService> _logger;

    public CloudinaryFileService(
        Cloudinary cloudinary,
        IHttpClientFactory httpClientFactory,
        IConfiguration configuration,
        ILogger<CloudinaryFileService> logger)
    {
        _cloudinary = cloudinary;
        _httpClientFactory = httpClientFactory;
        _folder = configuration.GetSection("Cloudinary")["Folder"];
        _logger = logger;
    }

    public async Task<string> UploadAsync(Stream fileStream, string fileName, string contentType)
    {
        ArgumentNullException.ThrowIfNull(fileStream);
        if (string.IsNullOrWhiteSpace(fileName))
            throw new ArgumentException("File name is required", nameof(fileName));

        var nameWithoutExt = Path.GetFileNameWithoutExtension(fileName);
        var uniqueId = $"{nameWithoutExt}_{Guid.NewGuid():N}";
        var publicId = string.IsNullOrWhiteSpace(_folder) ? uniqueId : $"{_folder}/{uniqueId}";

        if (fileStream.CanSeek) fileStream.Position = 0;

        var uploadParams = new ImageUploadParams
        {
            File = new FileDescription(fileName, fileStream),
            PublicId = publicId,
        };

        var result = await _cloudinary.UploadAsync(uploadParams);

        if (result is null)
            throw new InvalidOperationException("Cloudinary upload returned no response");

        if (result.Error is not null)
        {
            _logger.LogError("Cloudinary upload failed for {FileName}: {Error}", fileName, result.Error.Message);
            throw new InvalidOperationException($"Cloudinary upload error: {result.Error.Message}");
        }

        return result.PublicId ?? result.SecureUrl?.ToString() ?? publicId;
    }

    public async Task<Stream> DownloadAsync(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath))
            throw new ArgumentException("File path is required", nameof(filePath));

        var publicId = ExtractPublicId(filePath);
        var resource = await _cloudinary.GetResourceAsync(new GetResourceParams(publicId)
        {
            ResourceType = ResourceType.Auto
        });

        var url = resource?.SecureUrl?.ToString();
        if (string.IsNullOrWhiteSpace(url))
            throw new FileNotFoundException("Resource not found on Cloudinary", publicId);

        var http = _httpClientFactory.CreateClient();
        var response = await http.GetAsync(url);
        response.EnsureSuccessStatusCode();

        var memory = new MemoryStream();
        await response.Content.CopyToAsync(memory);
        memory.Position = 0;
        return memory;
    }

    public async Task<bool> DeleteAsync(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath))
            throw new ArgumentException("File path is required", nameof(filePath));

        var publicId = ExtractPublicId(filePath);

        try
        {
            var result = await _cloudinary.DestroyAsync(new DeletionParams(publicId)
            {
                ResourceType = ResourceType.Auto
            });

            return result?.Result is { } res
                && (res.Equals("ok", StringComparison.OrdinalIgnoreCase)
                    || res.Equals("deleted", StringComparison.OrdinalIgnoreCase)
                    || res.Equals("not_found", StringComparison.OrdinalIgnoreCase));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Cloudinary delete failed for {PublicId}", publicId);
            return false;
        }
    }

    public async Task<bool> ExistsAsync(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath))
            throw new ArgumentException("File path is required", nameof(filePath));

        var publicId = ExtractPublicId(filePath);
        try
        {
            var resource = await _cloudinary.GetResourceAsync(new GetResourceParams(publicId)
            {
                ResourceType = ResourceType.Auto
            });
            return resource is { } r && !string.IsNullOrWhiteSpace(r.PublicId);
        }
        catch
        {
            return false;
        }
    }

    private static string ExtractPublicId(string filePath)
    {
        if (!Uri.TryCreate(filePath, UriKind.Absolute, out var uri))
            return filePath;

        var segs = uri.AbsolutePath.Split('/', StringSplitOptions.RemoveEmptyEntries);
        for (var i = 0; i < segs.Length - 1; i++)
        {
            if (segs[i].Length > 1 && segs[i][0] == 'v' && int.TryParse(segs[i].AsSpan(1), out _))
            {
                var remaining = string.Join('/', segs[(i + 1)..]);
                var lastDot = remaining.LastIndexOf('.');
                if (lastDot > 0) remaining = remaining[..lastDot];
                return Uri.UnescapeDataString(remaining);
            }
        }

        var last = segs.Length > 0 ? segs[^1] : uri.AbsolutePath;
        var dot = last.LastIndexOf('.');
        if (dot > 0) last = last[..dot];
        return Uri.UnescapeDataString(last);
    }
}
