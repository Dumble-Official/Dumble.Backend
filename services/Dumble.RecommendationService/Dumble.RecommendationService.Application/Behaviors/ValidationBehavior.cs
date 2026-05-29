using FluentValidation;
using MediatR;

namespace Dumble.RecommendationService.Application.Behaviors;

/// <summary>
/// Runs any FluentValidation validators registered for a request before its handler.
/// A failure throws <see cref="ValidationException"/>, which the API's exception
/// mapping turns into a 400 problem+json response.
/// </summary>
public sealed class ValidationBehavior<TRequest, TResponse> : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
{
    private readonly IEnumerable<IValidator<TRequest>> _validators;

    public ValidationBehavior(IEnumerable<IValidator<TRequest>> validators)
    {
        _validators = validators;
    }

    public async Task<TResponse> Handle(
        TRequest request,
        RequestHandlerDelegate<TResponse> next,
        CancellationToken cancellationToken)
    {
        if (_validators.Any())
        {
            var context = new ValidationContext<TRequest>(request);
            var results = await Task.WhenAll(
                _validators.Select(v => v.ValidateAsync(context, cancellationToken)));

            var failures = results.SelectMany(r => r.Errors).Where(f => f is not null).ToList();
            if (failures.Count != 0)
                throw new ValidationException(failures);
        }

        return await next();
    }
}
